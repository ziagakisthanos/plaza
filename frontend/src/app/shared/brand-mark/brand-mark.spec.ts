import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BrandMark } from './brand-mark';

@Component({
  imports: [BrandMark],
  template: `<app-brand-mark tone="light" /><app-brand-mark /><app-brand-mark [size]="40" />`,
})
class Host {}

describe('BrandMark', () => {
  function marks(): HTMLElement[] {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('app-brand-mark'));
  }

  it('draws the logo at the requested size', () => {
    const svgs = marks().map((mark) => mark.querySelector('svg') as SVGElement);

    expect(svgs.map((svg) => svg.getAttribute('width'))).toEqual(['30', '30', '40']);
  });

  it('gives every logo its own gradient, so one hidden logo cannot break another', () => {
    const ids = marks().map((mark) => mark.querySelector('linearGradient')?.getAttribute('id'));

    expect(ids.every((id) => !!id)).toBe(true);
    expect(new Set(ids).size).toBe(3);
  });

  it('fills a normal logo with its own gradient', () => {
    const [, normal] = marks();

    const gradientId = normal.querySelector('linearGradient')?.getAttribute('id');

    expect(normal.querySelector('rect')?.getAttribute('fill')).toBe(`url(#${gradientId})`);
  });

  it('fills a light logo with plain white, for dark backgrounds', () => {
    const [light] = marks();

    expect(light.querySelector('rect')?.getAttribute('fill')).toBe('#ffffff');
    expect(light.querySelector('path')?.getAttribute('stroke')).toBe('#4b36ba');
  });
});
